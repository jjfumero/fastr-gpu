#!/bin/bash

NOW=""

function getDate() {
	NOW=$(date +"%m-%d-%Y-%H-%M-%S")
}

function runSaxpy {
	logFile=$1
    for size in 100 200 8388608 16777216 33554432 67108864 134217728
    do
        echo "Running saxpy with : $size" 
        ./runbench benchmarks/fastR/saxpy/$2 $size > ./$logFile
    done
}

function saxpy() {
	echo "CPU"
    getDate
    logFile="fastr_saxpyLogs_$NOW.log"	
	runSaxpy $logFile saxpy.R
	
	echo "GPU"
	getDate
    logFile="astx_saxpyLogs_$NOW.log"
	runSaxpy $logFile saxpyGPU.R
}

function runBS {
    # 1048576:67108864:*2
	logFile=$1
    for size in 1000 2000 1048576 2097152 4194304 8388608 16777216 33554432 67108864
    do
        echo "Running blackscholes with : $size" 
        ./runbench benchmarks/fastR/blackscholes/$2 $size > ./$logFile
    done
}

function blackscholes() {
	echo "FastR"
    getDate
    logFile="fastr_blackscholesLogs_$NOW.log"
	runBS $logFile blackscholes.R

	echo "GPU"
    getDate
    logFile="astx_blackscholesLogs_$NOW.log"
	runBS $logFile blackscholesGPU.R
}

function runMC {
	logFile=$1
    #262144:2097152;*2
    for size in 262144 524288 1048576 2097152
    do
        echo "Running montecarlo with : $size" 
        ./runbench benchmarks/fastR/montecarlo/$2 $size > ./$logFile
    done
}

function montecarlo() {
	echo "FastR"
    getDate
    logFile="fastr_montecarloLogs_$NOW.log"
	runMC $logFile montecarlo.R

	echo "GPU"
    getDate
    logFile="astx_montecarloLogs_$NOW.log"
	runMC $logFile montecarloGPU.R
}


# main
saxpy
blackscholes
##montecarlo	# pending
