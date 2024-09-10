work_path=`dirname $0`
cd ${work_path}

current_path=$(pwd)

envType="test"
envName="MT Test"

sh xdd_login.sh "${envType}" "${envName}"

token=$(cat "logs/xdd_login_${envType}.log" | grep 'doAction=' | grep -o -E '"token":"[^"]*"' | cut -d'"' -f4)
echo $token

sh ../bin/run.sh "${current_path}/config/mt_regtest.json" "${envName}" "XddPlat-RegTest" "token=${token}"
