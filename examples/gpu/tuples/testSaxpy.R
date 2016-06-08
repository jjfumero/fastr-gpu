a <- 1:1000
b <- 1:1000

gpuFunction <- function(x, y) {
	return (alpha * x + y)
}

alpha <- 2.5
seqResult <- alpha * a +  b 


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
    result <- alpha * a + b 
    end <- nanotime()
    print(end-start);
}

