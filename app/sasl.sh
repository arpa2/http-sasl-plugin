#!/bin/sh

#java -Dprism.order=j2d -jar ~/arpa2/http-sasl-plugin/app/target/SaslJavaClient-1.0-SNAPSHOT-jar-with-dependencies.jar
java -Dprism.order=j2d --module-path /root/.m2/repository/org/openjfx/javafx-base/11/javafx-base-11-linux.jar:/root/.m2/repository/org/openjfx/javafx-base/11/javafx-base-11.jar:/root/.m2/repository/org/openjfx/javafx-controls/11/javafx-controls-11-linux.jar:/root/.m2/repository/org/openjfx/javafx-controls/11/javafx-controls-11.jar:/root/.m2/repository/org/openjfx/javafx-graphics/11/javafx-graphics-11-linux.jar:/root/.m2/repository/org/openjfx/javafx-graphics/11/javafx-graphics-11.jar --add-modules javafx.base,javafx.controls,javafx.graphics -classpath /root/arpa2/http-sasl-plugin/app/target/classes:/root/.m2/repository/org/glassfish/javax.json/1.1/javax.json-1.1.jar nl.mansoft.sasl.Client
