@echo off

goto browser-client

:java-client
echo starting SaslJavaClient > plugin.log
java -jar target\SaslJavaClient-1.0-SNAPSHOT-jar-with-dependencies.jar 2>> plugin.log
goto end

:browser-client
echo starting browser-client.exe > plugin.log

set KIP_REALM=unicorn.demo.arpa2.org
set QUICKSASL_PASSPHRASE=sekreet
set KIPSERVICE_CLIENT_REALM=arpa2.net
set KIPSERVICE_CLIENTUSER_LOGIN=demo
set KIPSERVICE_CLIENTUSER_ACL=demo+ali
set SASL_CLIENT_REALM=arpa2.net
set SASL_CLIENTUSER_LOGIN=demo
set SASL_CLIENTUSER_ACL=demo+ali
set SASL_PATH=C:\msys64\mingw64\lib\sasl2

browser-client.exe 2>> plugin.log
:end
