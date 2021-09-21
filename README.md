# arutils
application bootstrap environment and bulk utils

Here is a quick tutorial https://github.com/AlexRaybosh/arutils_App_Env_tut
for application configurations

## Rational

Unified configuration, accross environments with fine grained control over various parmaters, and overrides from the database<br>
Reasonable separation of sensitive parameters (e.g. database password, rsa keys) into a host specific components. e.g. via SHELL_EVAL/FILE_EXEC bootstrap entries<br>
Easy to mange configuration modules, with specific configuration parameters<br>
Easy to reload configuration on the fly, without brining process/cluster down<br>
Self aware processes (on Linux) to tie-up to a particular cluster record<br>
Ability to easily add components, to synchronize accross a cluster using a database<br>
Build in API to speed-up slow/thick synchronous requests, for database, or a bulk APIs requests<br>
Database jdbc wrapper, with a build-in http profiler (collect quiry times realtime)<br>


## SQL
Few useful tables, to track process, dictionary, cluster
https://github.com/AlexRaybosh/arutils/tree/master/sql/mysql
