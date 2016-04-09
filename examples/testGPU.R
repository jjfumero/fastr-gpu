a <- 1:10000

f <- function(x) {
	a <- x * x
	return (x * x)
}

for (i in seq(1,10)) {
        start <- nanotime()
        result <- marawacc.testGPU(a, f, nThreads=4)
        end <- nanotime()
        print(end-start);
}

