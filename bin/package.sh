#/bin/bash
work_path=`dirname $0`
cd ${work_path}/../

mvn clean install -Dmaven.test.skip=true -s /Users/brucegao/Documents/Java/settings.xml

cp -f ./target/xdd-ct-3.8.7.jar ./bin/xdd-ct-3.8.7.jar
cp -rf ./target/lib ./bin/lib
