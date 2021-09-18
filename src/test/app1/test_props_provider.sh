#!/bin/bash

echo "num of args " "$#"
echo args "$@"

if [[ "$#" > 1 ]]; then
	echo "Not going to do " "$@" >&2
	exit 1
fi

echo "env=myenv"
echo "dburl=jdbc:mysql://localhost:3306/test2?autoReconnect=true&allowMultiQueries=true&cacheResultSetMetadata=true&emptyStringsConvertToZero=false&useInformationSchema=true&useServerPrepStmts=true&rewriteBatchedStatements=true"
echo "dbuser=test2"
echo "dbpassword="