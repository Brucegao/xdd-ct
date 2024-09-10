work_path=`dirname $0`
cd ${work_path}

current_path=$(pwd)

envType=$1
envName=$2
echo "envType=${envType}"
echo "envName=${envName}"

# 定义 logs 目录的路径
LOGS_DIR="logs"

# 检查 logs 目录是否存在
if [ ! -d "$LOGS_DIR" ]; then
    # 如果不存在,则创建目录
    mkdir "$LOGS_DIR"
    echo "created directory: $LOGS_DIR"
else
    echo "directory $LOGS_DIR already exists."
fi

token=$(cat "logs/xdd_login_${envType}.log" | grep 'doAction=' | grep -o -E '"token":"[^"]*"' | cut -d'"' -f4)
echo $token

sh ../bin/run.sh "${current_path}/config/xdd_user_login.json" "${envName}" "XddPlat" "token=${token}" "XddPlat:user-/getInfo" > "logs/xdd_getinfo_${envType}.log"

code=$(cat "logs/xdd_getinfo_${envType}.log" | grep 'doAction=' | grep -o -E '"code":[0-9]+' | cut -d':' -f2)
echo $code

if [ "$code" -eq 200 ]; then
    echo 'user login'
else
    echo 'user not login'
    echo 'to login ...'

    sh ../bin/run.sh "${current_path}/config/xdd_user_login.json" "${envName}" "XddPlat" "XddPlat:user-/login" > "logs/xdd_login_${envType}.log"

    token=$(cat "logs/xdd_login_${envType}.log" | grep 'doAction=' | grep -o -E '"token":"[^"]*"' | cut -d'"' -f4)
    echo $token
fi
