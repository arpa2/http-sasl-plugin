@echo off

goto browser-client

:java-client
echo starting SaslJavaClient > plugin.log
java -jar target\SaslJavaClient-1.0-SNAPSHOT-jar-with-dependencies.jar 2>> plugin.log
goto end

:browser-client
echo starting browser-client.exe > plugin.log

REM aeneas.vanrein.org
set KIP_REALM=unicorn.demo.arpa2.org
set SASL_CLIENTUSER_LOGIN=demo
set QUICKSASL_PASSPHRASE=sekreet

REM mansoft.nl
REM set KIP_REALM=Top secret supporting x-over
REM set SASL_CLIENTUSER_LOGIN=henri
REM set QUICKSASL_PASSPHRASE=1234

set KIPSERVICE_CLIENT_REALM=arpa2.net
set KIPSERVICE_CLIENTUSER_LOGIN=demo
set KIPSERVICE_CLIENTUSER_ACL=demo+ali
set SASL_CLIENT_REALM=arpa2.net
set SASL_CLIENTUSER_ACL=demo
set SASL_PATH=C:\msys64\mingw64\lib\sasl2
set SASL_CONF_PATH=c:\Users\hfman\Documents\arpa2\http-sasl-plugin\app

browser-client.exe 2>> plugin.log
:end
