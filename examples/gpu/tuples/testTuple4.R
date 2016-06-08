a <- 1:1000
b <- 1:1000
c <- 1:1000
d <- 1:1000

gpuFunction <- function(x, y, z, w) {
	return (x * y * z * w)
}

cpuFunction <- function(x, y, z, w) {
	return (x * y * z * w)
}

# Need this line for warming up, more time in the profiling
seqResult <- sapply(a, gpuFunction, b, c, d)
seqResult <- a * b * c * d

for (i in seq(1,20)) {

    start <- nanotime()
    result <- marawacc.testGPU(a, gpuFunction, b, c, d)
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
    result <- a * b * c * d
    end <- nanotime()
    print(end-start);
}

