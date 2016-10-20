# R test with function calls

alpha <- function(y) {
	return (y * 2.012)
}

f <- function(x) {
	return (x * alpha(x))
}

input <- 1:100000

for (i in 1:10) {
    start <- nanotime()
    result <- marawacc.testGPU(input, f)
    end <- nanotime()
    print(end-start);
}

