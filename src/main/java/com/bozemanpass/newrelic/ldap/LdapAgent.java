/* START COPY NOTICE
 * MIT License
 * Copyright (c) 2018 Bozeman Pass, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * END COPY NOTICE */

package com.bozemanpass.newrelic.ldap;

import com.newrelic.metrics.publish.Agent;
import com.newrelic.metrics.publish.configuration.ConfigurationException;
import com.newrelic.metrics.publish.processors.EpochProcessor;
import com.newrelic.metrics.publish.processors.Processor;
import com.newrelic.metrics.publish.util.Logger;
import org.json.simple.JSONObject;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.LdapContext;
import java.net.InetAddress;
import java.util.*;

public class LdapAgent extends Agent {
    private static final Logger log = Logger.getLogger(LdapAgent.class);

    private static final int DEFAULT_TIMEOUT = 10000;

    private static final String GUID = "com.bozemanpass.newrelic.ldap";
    private static final String VERSION = "1.0.2";

    private static final String MONITOR_DN = "cn=monitor";
    private static final String SNMP_DN = "cn=snmp,cn=monitor";
    private static final String DB_MONITOR_DN = "cn=database,cn=monitor,cn=ldbm database,cn=plugins,cn=config";
    private static final String BACKENDMONITOR_ATTR = "backendmonitordn";
    private static final String DBFILENAME_ATTR = "dbfilename";
    private static final String READWAITERS_ATTR = "readwaiters";
    private static final String BASIC_COUNTER_PREFIX = "LDAP";
    private static final String LDBM_PREFIX = "Database";
    private static final String BACKEND_COUNTER_PREFIX = "Backend";
    private static final String TIMEDOPS_PREFIX = "LDAP/TimedOps";
    private static final String COUNT_SUFFIX = "Count";
    private static final String RATE_SUFFIX = "Rate";

    private final String agentName;
    private final String host;
    private final int port;
    private final String binddn;
    private final String bindpw;
    private final boolean anonymousBind;
    private final boolean trustAnySSL;
    private final boolean useSSL;
    private final int timeout;
    private final Map<String, Object> config;
    private final Map<String, LdapMetric> ldapCounters;
    private final Map<String, LdapMetric> ldapGauges;
    private final Map<String, LdapMetric> ldbmCounters;
    private final Map<String, LdapMetric> ldbmGauges;
    private final Map<String, LdapMetric> backendCounters;
    private final Map<String, LdapMetric> backendGauges;
    private final Map<String, Processor> processors = new HashMap<>();
    private final Map<String, Processor> backendProcessors = new HashMap<>();
    private final Map<String, Processor> ldbmProcessors = new HashMap<>();

    private String searchbase = null;
    private String searchscope = null;
    private String searchfilter = null;
    private String modifydn = null;
    private String modifyattr = null;


    public LdapAgent(Map<String, Object> config) throws ConfigurationException {
        super(GUID, VERSION);

        try {
            this.config = Collections.unmodifiableMap(config);

            String s = (String) config.get("host");
            this.host = isNullOrEmpty(s) ? InetAddress.getLocalHost().getCanonicalHostName() : s;

            s = (String) config.get("name");
            this.agentName = !isNullOrEmpty(s) ? s : "LDAP - " + this.host;

            s = (String) config.get("binddn");
            this.binddn = !isNullOrEmpty(s) ? s : null;

            s = (String) config.get("bindpw");
            this.bindpw = !isNullOrEmpty(s) ? s : null;

            this.anonymousBind = isNullOrEmpty(binddn) || isNullOrEmpty(bindpw);

            Long i = (Long) config.get("port");
            this.port = null != i ? i.intValue() : 389;

            i = (Long) config.get("timeout");
            timeout = null != i ? i.intValue() : DEFAULT_TIMEOUT;

            Boolean b = (Boolean) config.get("trust_any_ssl");
            trustAnySSL = null != b ? b : false;

            b = (Boolean) config.get("use_ssl");
            useSSL = null != b ? b : 636 == this.port;

            JSONObject jo = (JSONObject) config.get("ldap");
            this.ldapCounters = Collections.unmodifiableMap(parseMetricSpecs((Map<String, String>) jo.get("counters")));
            this.ldapGauges = Collections.unmodifiableMap(parseMetricSpecs((Map<String, String>) jo.get("gauges")));

            for (String key : ldapCounters.keySet()) {
                processors.put(key, new EpochProcessor());
            }

            jo = (JSONObject) config.get("backendmonitor");
            if (null != jo) {
                this.backendCounters = Collections.unmodifiableMap(parseMetricSpecs((Map<String, String>) jo.get("counters")));
                this.backendGauges = Collections.unmodifiableMap(parseMetricSpecs((Map<String, String>) jo.get("gauges")));
            } else {
                this.backendGauges = Collections.emptyMap();
                this.backendCounters = Collections.emptyMap();
            }

            jo = (JSONObject) config.get("ldbm");
            if (null != jo) {
                this.ldbmCounters = Collections.unmodifiableMap(parseMetricSpecs((Map<String, String>) jo.get("counters")));
                this.ldbmGauges = Collections.unmodifiableMap(parseMetricSpecs((Map<String, String>) jo.get("gauges")));
            } else {
                this.ldbmCounters = Collections.emptyMap();
                this.ldbmGauges = Collections.emptyMap();
            }

            for (String key : ldbmCounters.keySet()) {
                ldbmProcessors.put(key, new EpochProcessor());
            }

            jo = (JSONObject) config.get("timedops");
            if (null != jo) {
                Map<String, String> m = (Map<String, String>) jo.get("search");
                if (null != m) {
                    s = m.get("base");
                    searchbase = !isNullOrEmpty(s) ? s : null;
                    s = m.get("filter");
                    searchfilter = !isNullOrEmpty(s) ? s : "(objectClass=*)";
                    s = m.get("scope");
                    searchscope = !isNullOrEmpty(s) ? s : "base";
                }

                m = (Map<String, String>) jo.get("modify");
                if (null != m) {
                    s = m.get("dn");
                    modifydn = !isNullOrEmpty(s) ? s : null;
                    s = m.get("attribute");
                    modifyattr = !isNullOrEmpty(s) ? s : "internationalisdnnumber";
                }
            }
        } catch (Throwable t) {
            throw new ConfigurationException(t);
        }
    }

    @Override
    public String getAgentName() {
        return agentName;
    }

    /**
     * The main method.  This is called at an interval of ~ 60 seconds by the Runner.
     */
    @Override
    public void pollCycle() {
        try {
            DirContext ctx = connect();
            try {
                processMainLdapCounters(ctx);

                // The backend monitor entry is not readable anonymously by default.
                if (!anonymousBind) {
                    processLdbmCounters(ctx);
                    processBackendDbCounters(ctx);
                }

                if (!isNullOrEmpty(searchbase)) {
                    reportSearchTime(ctx);
                }

                if (!isNullOrEmpty(modifydn) && !anonymousBind) {
                    reportModifyTime(ctx);
                }
            } finally {
                ctx.close();
            }
        } catch (Throwable t) {
            log.error(t, "Error polling!");
        }
    }

    /**
     * Report the main LDAP counters.  These are all the classics, like the number of searches, adds, etc.
     *
     * @param ctx the LDAP connection
     * @throws NamingException
     */
    private void processMainLdapCounters(DirContext ctx) throws NamingException {
        // We can get most of the basic metrics we need off the SNMP entry.
        // In a default installation of 389DS, this entry is readable anonymously.
        Map<String, Long> snmpCounters = parseMonitorEntry(ctx, SNMP_DN, ldapCounters, ldapGauges);

        for (Map.Entry<String, Long> entry : snmpCounters.entrySet()) {
            String key = entry.getKey();
            Long val = entry.getValue();
            if (ldapCounters.containsKey(key)) {
                LdapMetric cfg = ldapCounters.get(key);
                reportMetric(BASIC_COUNTER_PREFIX + "/" + cfg.metric + "/" + RATE_SUFFIX, cfg.unit
                        + "/sec", processors.get(key).process(val));
                reportMetric(BASIC_COUNTER_PREFIX + "/" + cfg.metric + "/" + COUNT_SUFFIX, cfg.unit, val);
            } else if (ldapGauges.containsKey(key)) {
                LdapMetric cfg = ldapGauges.get(key);
                reportMetric(BASIC_COUNTER_PREFIX + "/" + cfg.metric, cfg.unit, val);
            }
        }

        // The only basic metric we cannot find on the SNMP entry is 'readwaiters'
        if (ldapGauges.containsKey(READWAITERS_ATTR)) {
            LdapMetric cfg = ldapGauges.get(READWAITERS_ATTR);
            Map<String, Long> monitor = parseMonitorEntry(ctx, MONITOR_DN, ldapCounters, ldapGauges);
            reportMetric(BASIC_COUNTER_PREFIX + "/" + cfg.metric, cfg.unit, monitor.get(READWAITERS_ATTR));
        }
    }

    /**
     * Report the overall DB metrics.
     *
     * @param ctx the LDAP connection
     * @throws NamingException
     */
    private void processLdbmCounters(DirContext ctx) throws NamingException {
        Map<String, Long> ldbm = parseMonitorEntry(ctx, DB_MONITOR_DN, ldbmCounters, ldbmGauges);
        for (Map.Entry<String, Long> entry : ldbm.entrySet()) {
            String key = entry.getKey();
            Long val = entry.getValue();
            if (ldbmCounters.containsKey(key)) {
                LdapMetric cfg = ldbmCounters.get(key);
                reportMetric(LDBM_PREFIX + "/" + cfg.metric + "/" + RATE_SUFFIX, cfg.unit
                        + "/sec", ldbmProcessors.get(key).process(val));
                reportMetric(LDBM_PREFIX + "/" + cfg.metric + "/" + COUNT_SUFFIX, cfg.unit, val);
            } else if (ldbmGauges.containsKey(key)) {
                LdapMetric cfg = ldbmGauges.get(key);
                reportMetric(LDBM_PREFIX + "/" + cfg.metric, cfg.unit, val);
            }
        }
    }

    /**
     * Discover and report the per-backend DB metrics.
     *
     * @param ctx the LDAP connection
     * @throws NamingException
     */
    private void processBackendDbCounters(DirContext ctx) throws NamingException {
        List<String> backendMonitors = lookupBackendEntryDNs(ctx);
        for (String backendMonitorDn : backendMonitors) {
            //this is kind of hackish, but the entry will look something like:
            // cn=monitor,cn=userRoot,cn=ldbm database,cn=plugins,cn=config
            //    or
            // cn=monitor,cn=NetscapeRoot,cn=ldbm database,cn=plugins,cn=config

            String backendName = backendMonitorDn.split(",")[1].split("=", 2)[1];
            LdapContext entryCtx = (LdapContext) ctx.lookup(backendMonitorDn);
            NamingEnumeration attrs = entryCtx.getAttributes("").getAll();

            // There are multiple DB files, each with a set of metrics.
            // The attributes on the monitor entry look something like:
            //    dbfilename-15: userRoot/givenName.db
            //    dbfilecachehit-15: 825858
            //    ...
            // We want to strip the number from the attribute agentName and report
            // all matching metrics and values under the appropriate
            // filename (eg, userRoot/givenName.db4).

            Map<String, Attribute> mappedAttrs = new HashMap<>();
            while (attrs.hasMore()) {
                Attribute attr = (Attribute) attrs.next();
                mappedAttrs.put(attr.getID(), attr);
            }

            for (Map.Entry<String, Attribute> entry : mappedAttrs.entrySet()) {
                Attribute attr = entry.getValue();
                String longName = entry.getKey();
                String shortName = attr.getID().toLowerCase().split("-", 2)[0];

                if (DBFILENAME_ATTR.equalsIgnoreCase(shortName)) {
                    continue;
                }

                String currentDbFile = null;
                if (longName.matches(".*-[0-9]+$")) {
                    String[] parts = longName.split("-", 2);
                    currentDbFile = mappedAttrs.get(DBFILENAME_ATTR + "-" + parts[1]).get().toString().split("/", 2)[1];
                }

                if (backendCounters.containsKey(shortName)) {
                    Long val = Long.parseLong(attr.get().toString());
                    LdapMetric spec = backendCounters.get(shortName);

                    String calculatedName = backendName + "/" + spec.metric;
                    if (spec.metric.contains("%s")) {
                        calculatedName = String.format(calculatedName, currentDbFile);
                    }

                    Processor p = backendProcessors.get(calculatedName);
                    if (null == p) {
                        p = new EpochProcessor();
                        backendProcessors.put(calculatedName, p);
                    }

                    reportMetric(BACKEND_COUNTER_PREFIX + "/" + calculatedName + "/" + RATE_SUFFIX, spec.unit
                            + "/sec", p.process(val));
                    reportMetric(BACKEND_COUNTER_PREFIX + "/" + calculatedName + "/" + COUNT_SUFFIX, spec.unit, val);
                } else if (backendGauges.containsKey(shortName)) {
                    Long val = Long.parseLong(attr.get().toString());
                    LdapMetric spec = backendGauges.get(shortName);

                    String calculatedName = backendName + "/" + spec.metric;
                    if (calculatedName.contains("%s")) {
                        calculatedName = String.format(calculatedName, currentDbFile);
                    }

                    reportMetric(BACKEND_COUNTER_PREFIX + "/" + calculatedName, spec.unit, val);
                }
            }
        }
    }

    /**
     * Modify an entry and record the time taken.
     *
     * @param ctx the LDAP connection
     * @throws NamingException
     */
    private void reportModifyTime(DirContext ctx) throws NamingException {
        ModificationItem mod = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                new BasicAttribute(modifyattr, "New Relic " + new Date().toString()));

        long start = System.currentTimeMillis();
        ctx.modifyAttributes(modifydn, new ModificationItem[]{mod});
        long stop = System.currentTimeMillis();

        reportMetric(TIMEDOPS_PREFIX + "/" + "Modify/Time", "milliseconds", stop - start);
    }

    /**
     * Search and record the time taken.
     *
     * @param ctx the LDAP connection
     * @throws NamingException
     */
    private void reportSearchTime(DirContext ctx) throws NamingException {
        SearchControls ctls = new SearchControls();

        int scope = SearchControls.SUBTREE_SCOPE;
        if ("base".equalsIgnoreCase(searchscope)) {
            scope = SearchControls.OBJECT_SCOPE;
        } else if ("one".equalsIgnoreCase(searchscope)) {
            scope = SearchControls.ONELEVEL_SCOPE;
        }

        ctls.setSearchScope(scope);
        ctls.setTimeLimit(timeout);

        long start = System.currentTimeMillis();
        NamingEnumeration ne = ctx.search(searchbase, searchfilter, ctls);
        long stop = System.currentTimeMillis();

        int howMany = 0;
        try {
            while (ne.hasMore()) {
                ne.next();
                howMany++;
            }
        } finally {
            ne.close();
        }

        reportMetric(TIMEDOPS_PREFIX + "/" + "Search/Time", "milliseconds", stop - start);
        reportMetric(TIMEDOPS_PREFIX + "/" + "Search/Results", "entries", howMany);
    }

    /**
     * Connect and bind the to the LDAP server
     *
     * @return the LDAP connection DirContext
     * @throws NamingException
     */
    private DirContext connect() throws NamingException {
        Hashtable env = new Hashtable();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(timeout));
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(timeout));

        if (!anonymousBind) {
            env.put(Context.SECURITY_PRINCIPAL, binddn);
            env.put(Context.SECURITY_CREDENTIALS, bindpw);
        }


        StringBuilder url = new StringBuilder();
        if (useSSL) {
            url.append("ldaps://");
            env.put(Context.SECURITY_PROTOCOL, "ssl");
            if (trustAnySSL) {
                env.put("java.naming.ldap.factory.socket",
                        "com.bozemanpass.newrelic.ldap.util.DummySSLSocketFactory");
            }
        } else {
            url.append("ldap://");
        }

        url.append(host).append(":").append(port);

        env.put(Context.PROVIDER_URL, url.toString());

        log.debug(String.format("Connecting %s @ %s ...", anonymousBind ? "ANON" : binddn, url.toString()));

        return new InitialDirContext(env);
    }

    /**
     * Used for parsing the standard cn=monitor and cn=snmp,cn=monitor metrics.
     *
     * @param ctx the LDAP connection
     * @param dn  the DN of the monitor entry to lookup
     * @return
     * @throws NamingException
     */
    private Map<String, Long> parseMonitorEntry(DirContext ctx, String dn, Map<String, LdapMetric> counters, Map<String, LdapMetric> gauges) throws NamingException {
        Map<String, Long> ret = new HashMap<>();
        LdapContext entryCtx = (LdapContext) ctx.lookup(dn);
        NamingEnumeration attrs = entryCtx.getAttributes("").getAll();
        while (attrs.hasMore()) {
            Attribute attr = (Attribute) attrs.next();
            // These are all single-valued, numeric attributes
            String name = attr.getID().toLowerCase();
            if (counters.containsKey(name) || gauges.containsKey(name)) {
                Object value = attr.get();
                if (null != value) {
                    try {
                        ret.put(name, Long.parseLong(value.toString()));
                    } catch (Throwable t) {
                        log.warn(String.format("Forced to skip attribute: %s", attr.getID()));
                    }
                }
            }
        }
        return ret;
    }

    /**
     * The DN of the backend monitor entry is stored as an attribute on cn=monitor
     *
     * @param ctx the LDAP connection
     * @return the DN of the backend monitor entry
     * @throws NamingException
     */
    private List<String> lookupBackendEntryDNs(DirContext ctx) throws NamingException {
        LdapContext entryCtx = (LdapContext) ctx.lookup(MONITOR_DN);
        Attribute attr = entryCtx.getAttributes("").get(BACKENDMONITOR_ATTR);
        List<String> ret = new LinkedList<>();
        NamingEnumeration values = attr.getAll();
        while (values.hasMore()) {
            ret.add(values.next().toString());
        }
        return ret;
    }


    /**
     * Helper for holding the definitions of the metrics we are interested in.
     */
    private static class LdapMetric {
        final String ldapAttr;
        final String metric;
        final String unit;

        LdapMetric(String ldapAttr, String metric, String unit) {
            this.ldapAttr = ldapAttr;
            this.metric = metric;
            this.unit = unit;
        }
    }

    /**
     * Parse out all the metric specifications.
     *
     * @param input the metric specification as a map of strings
     * @return
     */
    private Map<String, LdapMetric> parseMetricSpecs(Map<String, String> input) {
        Map<String, LdapMetric> ret = new HashMap<>();
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = entry.getKey();
            String[] parts = entry.getValue().split(";", 2);
            ret.put(key, new LdapMetric(key, parts[0], parts[1]));
        }
        return ret;
    }

    private static boolean isNullOrEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }
}
