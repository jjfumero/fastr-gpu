
size <- 500 

REPETITIONS <- 1

## Lambda expression for the computation
benchmark <- function(inputSize) {

	saxpyFunction <- function(x) {
		result <- (0.12 * x);
		return (result);
	}	


	#x <- flagSequence(1, 2, size)
	x <- compassSequence(1, size, 2)

	print(x)

	
	for (i in 1:REPETITIONS) {
		system.gc()
		start <- nanotime()
		result <- marawacc.gpusapply(x, saxpyFunction);
		total <- nanotime() - start
		print(paste("Total Time: ", total))
	}
}

## Main 
print("ASTx GPU")
print(paste("SIZE:", size))
benchmark(size)

