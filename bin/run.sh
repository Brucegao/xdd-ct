work_path=`dirname $0`
cd ${work_path}

_jar=`ls lib | grep "..*\.zip$"`
_zip=`ls lib | grep "..*\.jar$"`
_classpath="${_jar} ${_zip}"
classpath=`echo ${_classpath} | sed -e 's/ /:lib\//g'`

echo lib/${classpath}

#java -Xms128m -Xmx512m -Dsupport_ds=true -Djava.library.path=./lib/jdic_linux -cp classes com.goojia.crawl.CrawlTestFrame
#java -Xms128m -Xmx512m -Dsupport_ds=true -Djava.library.path=./lib/jdic_linux -cp classes:lib/${classpath} com.goojia.crawl.WebCrawlApp
java -cp xdd-ct-3.8.7.jar:lib/${classpath} com.xdd.ct.framework.ApiTester "$1" "$2" "$3" "$4" "$5"
