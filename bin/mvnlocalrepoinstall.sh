#!/bin/sh
LIBDIR=$( cd "$( dirname "$0" )/../lib" && pwd )
mvn install:install-file -DgroupId=twocaptcha -DartifactId=twocaptcha -Dversion=1.0.0 -Dpackaging=jar -Dfile=$LIBDIR/2captcha_api-1.0.0.jar -Durl=file:repo
