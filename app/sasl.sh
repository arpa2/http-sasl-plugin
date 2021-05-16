#!/bin/sh

#java --module-path /root/.m2/repository/org/openjfx/javafx-base/11/javafx-base-11-linux.jar:/root/.m2/repository/org/openjfx/javafx-base/11/javafx-base-11.jar:/root/.m2/repository/org/openjfx/javafx-controls/11/javafx-controls-11-linux.jar:/root/.m2/repository/org/openjfx/javafx-controls/11/javafx-controls-11.jar:/root/.m2/repository/org/openjfx/javafx-graphics/11/javafx-graphics-11-linux.jar:/root/.m2/repository/org/openjfx/javafx-graphics/11/javafx-graphics-11.jar --add-modules javafx.base,javafx.controls,javafx.graphics -classpath ~/arpa2/http-sasl-plugin/app/target/SaslJavaClient-1.0-SNAPSHOT-jar-with-dependencies.jar nl.mansoft.sasl.Client

echo starting browser-client > plugin.log

# aeneas.vanrein.org
export KIP_REALM=pixie.demo.arpa2.org
export SASL_CLIENTUSER_LOGIN=demo
export QUICKSASL_PASSPHRASE=sekreet

# mansoft.nl
#export KIP_REALM='Top secret supporting x-over'
#export SASL_CLIENTUSER_LOGIN=henri
#export QUICKSASL_PASSPHRASE=1234

export KIPSERVICE_CLIENT_REALM=arpa2.net
export KIPSERVICE_CLIENTUSER_LOGIN=demo
export KIPSERVICE_CLIENTUSER_ACL=demo+ali
export SASL_CLIENT_REALM=arpa2.net
export SASL_CLIENTUSER_ACL=demo
#export SASL_PATH=C:\msys64\mingw64\lib\sasl2

/home/manson/arpa2/kip-work-henri/build/src/browser-client 2>> plugin.log
