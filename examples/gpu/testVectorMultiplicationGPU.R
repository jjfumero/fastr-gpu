a <- 1:100000
b <- 1:100000

gpuFunction <- function(x, y) {
	return (x * y)
}

cpuFunction <- function(x, y) {
	return (x * y)
}


seqResult <- sapply(a, gpuFunction, b)

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
    result <- sapply(a, cpuFunction, b)
    end <- nanotime()
    print(end-start);
}

