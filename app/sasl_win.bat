@echo off

goto :java-client

:java-client
echo starting SaslJavaClient > plugin.log
java -Djava.util.logging.config.file=logging.properties -Djava.security.auth.login.config=jaas-krb5.conf -Djava.security.krb5.conf=arpa2.conf -jar target\SaslJavaClient-1.0-SNAPSHOT-jar-with-dependencies.jar 2>> plugin.log
goto end

:browser-client

REM aeneas.vanrein.org
set KIP_REALM=unicorn.demo.arpa2.org
set SASL_CLIENTUSER_LOGIN=demo
set QUICKSASL_PASSPHRASE=sekreet

REM local java servlet
REM set KIP_REALM=192.168.2.100
REM set SASL_CLIENTUSER_LOGIN=henri
set QUICKSASL_PASSPHRASE=1234

set KIPSERVICE_CLIENTUSER_LOGIN=demo
set KIPSERVICE_CLIENTUSER_ACL=demo+ali
set KIPSERVICE_CLIENT_REALM=unicorn.demo.arpa2.org

REM set KIPSERVICE_CLIENTUSER_LOGIN=henri
REM set KIPSERVICE_CLIENTUSER_ACL=henri
REM set KIPSERVICE_CLIENT_REALM=unicorn.demo.arpa2.org

set SASL_REALM=192.168.2.100
set SASL_CLIENT_REALM=arpa2.net
set SASL_CLIENTUSER_ACL=henri
set SASL_PATH=C:\msys64\mingw64\lib\sasl2
set SASL_CONF_PATH=c:\Users\hfman\Documents\arpa2\http-sasl-plugin\app

set HTTP_SERVICE_NAME=HTTP
REM set HTTP_SERVICE_NAME=RealmCrossover

set PATH=c:\msys64\mingw64\bin;c:\msys64\mingw64\local\bin;c:\Qt\5.15.2\mingw81_64\bin
echo starting browser-client.exe > plugin.log
REM c:\msys64\mingw64\local\bin\browser-client.exe 2>> plugin.log
REM echo starting sasl-login.exe > plugin.log
C:\Users\hfman\Documents\Qt\http_sasl_client\build\native\sasl-login.exe 2>> plugin.log
REM c:\Users\hfman\Documents\Qt\build-http_sasl_client-ARPA2_Desktop_Qt_5_15_2_MinGW_64_bit-Debug\native\sasl-login.exe 2>> plugin.log

:end
