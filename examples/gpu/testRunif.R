a <- 1:10000

f <- function(x) {
	a <- runif(x)
	return (a[1] * x)
}

g <- function(x) {
	a <- runif(x)
	return (a * x)
}

seqResult <- sapply(a, f)

for (i in seq(1,20)) {

    start <- nanotime()
    result <- marawacc.testGPU(a, f)
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
    result <- sapply(a, g)
    end <- nanotime()
    print(end-start);
}

