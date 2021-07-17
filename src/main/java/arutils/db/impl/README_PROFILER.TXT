Environment:

	Disable profiler:
		DB_PROFILER=none
	
	Custom port range and direction:
		DB_PROFILER_WEB_PORT_START=(start port inclusive)
		DB_PROFILER_WEB_PORT_END=(end port inclusive)
		
		e.g. by default searching for a port to bind in a range:
			DB_PROFILER_WEB_PORT_START=65535, and  DB_PROFILER_WEB_PORT_END=1024
			
			
	Listen on interface with host name:	
		DB_PROFILER_WEB_HOST=
		
		If none defined, DB_PROFILER_WEB_HOST=localhost
		Wildcard: DB_PROFILER_WEB_HOST=*
		
	Prints out listening port
		DB_PROFILER_WEB_PORT_PRINT=(stdout|stderr)
		
		e.g:
		DB_PROFILER_WEB_PORT_PRINT=stdout - Prints port out to standard output 
		DB_PROFILER_WEB_PORT_PRINT=stderr - Prints port out to standard error