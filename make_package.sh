#!/bin/bash

gradle clean jar

cd build
BUILD_JAR=`ls libs/*.jar | head -1`
BUILD_NAME=`basename $BUILD_JAR | sed 's/\.jar$//'`

mkdir $BUILD_NAME
cp $BUILD_JAR $BUILD_NAME/plugin.jar
mkdir $BUILD_NAME/config
cp resources/main/config/*template* $BUILD_NAME/config/
cp resources/main/config/plugin.template.json $BUILD_NAME/config/plugin.json

tar czvf $BUILD_NAME.tar.gz $BUILD_NAME
