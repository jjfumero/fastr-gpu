a <- 1:100000

f <- function(x) {
	return (x * 2.0)
}

seqResult <- sapply(a, f)

for (i in seq(1,10)) {
    start <- nanotime()
    result <- marawacc.testGPU(a, f, nThreads=4)
    end <- nanotime()
    print(end-start);
	print(identical(seqResult, result))
}

