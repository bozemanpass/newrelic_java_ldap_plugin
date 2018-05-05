# Description

This plugin provides metrics for 389 Directory Server (http://www.port389.org).  A wide range of metrics are provided for LDAP basic operations (search, add, modify, delete, etc.), connections, and binds, as well as database and backend statistics.  The response time to perform sample search and modify operations can also be tracked.

![Screenshot][screenshot]

For additional documentation see the GitHub project page: https://github.com/bozemanpass/newrelic_java_ldap_plugin 

----

# Installation

To install using NPI (recommended) simply run:

    npi install com.bozemanpass.newrelic.ldap
    
See the [New Relic NPI](https://docs.newrelic.com/docs/plugins/plugins-new-relic/installing-plugins/installing-npi-compatible-plugin) documentation for further information about using NPI or the [New Relic plugin documentation](https://docs.newrelic.com/docs/plugins/plugins-new-relic/installing-plugins/installing-plugin) if installing manually.

# Plugin Configuration

By default, the plugin will try to connect to `localhost` on port `389` anonymously.  This can provide basic information like the number of searches, adds, etc.; however, most of the database and backend metrics are only available when doing an authenticated bind.  

It is important never to use `cn=Directory Manager`.  You should use a non-privileged account with read-only access to the monitoring entries (see Server Configuration).

To configure the connection information, including bind credentials, edit `config/plugin.json`.  For example:

      "host": "myldap.mydomain.local",
      "port": 389,
      "use_ssl": false,
      "binddn": "cn=newrelic,ou=monitoring,dc=mydomain,dc=local",
      "bindpw": "mypassword",

If using SSL with a self-signed certificate, set both `use_ssl` and `trust_any_ssl` to `true`:

      "host": "myldap.mydomain.local",
      "port": 636,
      "use_ssl": true,
      "trust_any_ssl": true,
 
The `timedops/modify` metric, which reports the time in milliseconds to do a sample modification, will only be attempted if the DN of the entry to modify is specified.  For example (using the same account for a self-modify):

        "modify": {
          "dn": "cn=newrelic,ou=monitoring,dc=mydomain,dc=local",
          "attribute": "internationalisdnnumber"
        }
        
----

# Server Configuration

You should never use a privileged account for monitoring.  It is customary to assign the required privileges to a group, and then add a user account to the indicated group.  The following example ACIs would enable read access (replace <MY_GROUP_DN> with the DN of your monitoring group):

    dn: cn=monitor
    changetype: modify
    add: aci
    aci: (target ="ldap:///cn=monitor*")
     (targetattr != "aci")(version 3.0; acl "Allow read access to Monitoring users"; 
     allow( read, search, compare ) groupdn="ldap:///<MY_GROUP_DN_GOES_HERE>";)
    
    dn: cn=config
    changetype: modify
    add: aci
    aci: (target ="ldap:///cn=monitor,cn=*,cn=ldbm database,cn=plugins,cn=config")
     (targetattr != "aci")(version 3.0; acl "Allow read access to Monitoring users"; 
     allow( read, search, compare ) groupdn="ldap:///<MY_GROUP_DN_GOES_HERE>"";)
    
    dn: cn=monitor,cn=ldbm database,cn=plugins,cn=config
    changetype: modify
    add: aci
    aci: (target ="ldap:///cn=*")
     (targetattr != "aci")(version 3.0; acl "Allow read access to Monitoring users"; 
     allow( read, search, compare ) groupdn="ldap:///<MY_GROUP_DN_GOES_HERE>"";)

----

# The Metrics

Descriptions are from the [Red Hat Directory Server documentation](https://access.redhat.com/documentation/en-us/red_hat_directory_server/10/html/administration_guide/monitoring_ds_using_snmp).

LDAP Metrics:

| Metric | Type | Description |
| :--- | :--- | :--- |
| Binds/Anonymous | Counter | The number of anonymous binds to the directory since server startup. |
| Binds/Simple | Counter | The number of binds to the directory that were established using a simple authentication method (such as password protection) since server startup. | 
| Binds/Strong | Counter | The number of binds to the directory that were established using a strong authentication method (such as TLS or a SASL mechanism like Kerberos) since server startup. |
| Bytes/Received | Counter | The number of bytes received since server startup. |
| Bytes/Sent | Counter | The number of bytes sent since server startup. |
| Connections/Current | Gauge | The number of currently open connections to the server. |
| Connections/Total | Counter | The total number of connections opened, including both currently open and closed connections, since server startup. |
| Entries/Sent | Counter | The number of entries returned as search results since server startup. |
| Errors/Binds | Counter | The number of bind requests that have been rejected by the directory due to authentication failures or invalid credentials since server startup. |
| Errors/General | Counter | The number of requests that could not be serviced due to errors (other than security or referral errors). Errors include name errors, update errors, attribute errors, and service errors. Partially serviced requests will not be counted as an error. |
| Errors/Security | Counter | The number of operations forwarded to this directory that did not meet security requirements. |
| Read Waiters/Current | Gauge | The number of connections where some requests are pending and not currently being serviced by a thread in Directory Server. |
| Referrals/Sent | Counter | The number of referrals returned by this directory in response to client requests since server startup. |
| Requests/Add | Counter | The number of add operations serviced by this directory since server startup. |
| Requests/Compare | Counter | The number of compare operations serviced by this directory since server startup. |
| Requests/Delete | Counter | The number of delete operations serviced by this directory since server startup. |
| Requests/Modify | Counter | The number of modify operations serviced by this directory since server startup. |
| Requests/modrdn | Counter | The number of modify RDN operations serviced by this directory since server startup. |
| Requests/Search | Counter | The total number of search operations serviced by this directory since server startup. |
| Requests/Search/OneLevel | Counter | The number of one-level search operations serviced by this directory since server startup. |
| Requests/Search/Subtree | Counter | The number of whole subtree search operations serviced by this directory since server startup. |
| Requests/Total | Counter | The total number of all requests received by the server since startup. |

Backend Instance Metrics:

| Metric | Type | Description |
| :--- | :--- | :--- |
| DB Cache/FILENAME/Hits | Counter | The number of times the database cache successfully supplied a requested page. |
| DB Cache/FILENAME/Misses | Counter | The number of times that a search result failed to hit the cache on this specific file. That is, a search that required data from this file was performed, and the required data could not be found in the cache. |
| DB Cache/FILENAME/PageIn | Counter | The number of pages brought to the cache from this file. |
| DB Cache/FILENAME/PageOut | Counter | The number of pages for this file written from cache to disk. | 
| DN Cache/CurrentCount | Gauge | The current number of items in the DN cache. |
| DN Cache/CurrentSize | Gauge | The current size in bytes of the DN cache. |
| DN Cache/HitRatio | Gauge | The ratio of hits to tries of the DN cache. |
| DN Cache/Hits | Counter | The total number of successful DN cache lookups. |
| DN Cache/MaxSize | Gauge | The maximum size in bytes of the DN cache. |
| DN Cache/Tries | Counter | The total number of DN cache lookups since the directory was last started. |
| Entry Cache/CurrentCount | Gauge | The number of directory entries currently present in the entry cache. |
| Entry Cache/CurrentSize | Gauge | The total size in bytes of directory entries currently present in the entry cache. |
| Entry Cache/HitRatio | Gauge | The ratio of database cache hits to database cache tries. The closer this value is to 100%, the better. |
| Entry Cache/Hits | Counter | The total number of successful entry cache lookups. That is, the total number of times the server could process a search request by obtaining data from the cache rather than by going to disk. |
| Entry Cache/MaxSize | Gauge | The maximum size of the entry cache maintained by the directory. |
| Entry Cache/Tries | Counter | The total number of entry cache lookups since the directory was last started. That is, the total number of entries requested since server startup. |
| Normalized DN Cache/CurrentCount | Gauge | The current number of items in the normalized DN cache. |
| Normalized DN Cache/CurrentSize | Gauge | The current size in bytes of the normalized DN cache. |
| Normalized DN Cache/Evictions | Counter | The number of items evicted from the normalized DN cache since server startup. |
| Normalized DN Cache/HitRatio | Gauge | The ratio of hits to tries for the normalized DN cache. |
| Normalized DN Cache/Hits | Counter | The total number of successful normalized DN cache lookups. |
| Normalized DN Cache/MaxSize | Gauge | The maximum size in bytes of the normalized DN cache. |
| Normalized DN Cache/Misses | Counter | The total number of unsuccessful normalized DN cache lookups. |
| Normalized DN Cache/Tries | Counter | The total number of normalized DN cache lookups. |

LDBM Metrics:

| Metric | Type | Description |
| :--- | :--- | :--- |
| Cache/CurrentSize | Gauge | The total cache size in bytes. |
| Cache/Hits | Counter | The requested pages found in the cache. |
| Cache/RegionWait | Counter | The number of times that a thread of control was forced to wait before obtaining the region lock.|
| Cache/Tries | Counter | The total cache lookups. |
| Hash/Buckets | Gauge | The number of hash buckets in buffer hash table. |
| Hash/Examined | Counter | The total number of hash elements traversed during hash table lookups.  |
| Hash/LongestChain | Gauge | The longest chain ever encountered in buffer hash table lookups. |
| Hash/Lookups | Counter | The total number of buffer hash table lookups. |
| Locks/Conflicts | Counter | The total number of locks not immediately available due to conflicts. |
| Locks/Current | Gauge | Number of locks currently used by the database.|
| Locks/Deadlocks | Counter | The number of deadlocks detected. |
| Locks/Lockers | Gauge | The number of current lockers. |
| Locks/Max | Gauge |Maximum number of locks used by the database since the last startup. |
| Locks/Objects/Current | Gauge | The current number of lock objects. |
| Locks/Objects/Max | Gauge | The maximum number of lock objects. |
| Locks/RegionWait | Counter | The number of times that a thread of control was forced to wait before obtaining the region lock. |
| Locks/Requested | Counter | The total number of locks requested. |
| Log/RegionWait | Counter | The number of times that a thread of control was forced to wait before obtaining the region lock. |
| Log/SinceCheckpoint | Gauge | The number of bytes written to this log since the last checkpoint. |
| Log/Written | Counter | The number of bytes written to this log. |
| Pages/Clean | Gauge | The clean pages currently in the cache. |
| Pages/Create | Counter | The pages created in the cache. |
| Pages/Dirty | Gauge | The dirty pages currently in the cache. |
| Pages/Evict/Clean | Counter | The clean pages forced from the cache. |
| Pages/Evict/Dirty | Counter | The dirty pages forced from the cache. |
| Pages/InUse | Gauge | All pages, clean or dirty, currently in use. |
| Pages/Read | Counter | The pages read into the cache.  |
| Pages/Trickle | Counter | The dirty pages written using the memp_trickle interface. |
| Pages/Write | Counter | The pages read into the cache. |
| Transactions/Aborted | Counter | The number of transactions that have been aborted.  |
| Transactions/Active | Gauge | The number of transactions that are currently active.  |
| Transactions/Committed | Counter | The number of transactions that have been committed. |
| Transactions/RegionWait | Counter | The number of times that a thread of control was forced to wait before obtaining the region lock. |

Timed Operation Metrics:

| Metric | Description |
| :--- | :--- |
| TimedOps/Modify | The time in milliseconds to perform the modification. |
| TimedOps/Search | The time in milliseconds to perform the search. |

---

# Requirements

This plugin requires Java JRE 1.8 or higher.

----

# Building from Source

To build the plugin JAR for testing or debugging, run:

    gradle clean jar
    
To create a full tarball suitable for distribution or deployment, run:

    ./make_package.sh

---

# License

MIT License

Copyright (c) 2018 Bozeman Pass, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

----

# Support

Contact [Bozeman Pass, Inc.](https://www.bozemanpass.com/contact-us/) with questions.

----

[screenshot]: https://raw.githubusercontent.com/bozemanpass/newrelic_java_ldap_plugin/master/screenshot.png "Screenshot"