a <- 1:1000
b <- 1:1000

gpuFunction <- function(x, y) {
	return (x - y)
}

cpuFunction <- function(x, y) {
	return (x - y)
}

# Need this line for warming up, more time in the profiling
#seqResult <- sapply(a, gpuFunction, b)
seqResult <- a - b 

for (i in seq(1,20)) {

    start <- nanotime()
    result <- marawacc.testGPU(a, gpuFunction, b)
    end <- nanotime()
    print(end-start);
	
	nonError <- identical(seqResult, result)
	if (!nonError) {
		for (i in seq(result)) {
			if (abs(seqResult[i] - result[i]) > 0.1) {
				print(nonError)
				break;
			}
		}	
	} else {
		print(nonError) 
	}

}

for (i in seq(1,20)) {
    start <- nanotime()
    result <- a - b 
    end <- nanotime()
    print(end-start);
}

