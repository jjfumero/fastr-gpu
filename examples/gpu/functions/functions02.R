# R test with function calls

alpha <- function(y) {
	return (y * 2.012)
}

f <- function(x) {
	y <- alpha(x) * x
	z <- sqrt(y) * x
	return (z)
}

input <- 1:100000

for (i in 1:10) {
    start <- nanotime()
    result <- marawacc.testGPU(input, f)
    end <- nanotime()
    print(end-start);
}

