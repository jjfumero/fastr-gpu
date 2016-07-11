a <- 1:1000
b <- 1:1000

gpuFunction <- function(x, y) {
	return (alpha * x + y)
}

cpuFunction <- function(x, y) {
	return (alpha * x + y)
}

# Need this line for warming up, more time in the profiling
#seqResult <- sapply(a, gpuFunction, b)

alpha <- 0.12
seqResult <- alpha * a + b

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

