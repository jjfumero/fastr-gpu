
gpuFunction <- function(x) {
	foo <- 0
	for (i in 1:size) {
		b <- x * scope[i] 
		foo <- foo + b
	}
	return (foo)
}

# Inputs
size <- 1000
scope <- runif(1000)
#scope <- 1001:2000
a <- 1:size

for (i in seq(1,2)) {
    start <- nanotime()
    result <- marawacc.gpusapply(a, gpuFunction)
    end <- nanotime()
    print(end-start);

}

