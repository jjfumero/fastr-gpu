input <- 1:10000
output<- 1:10000

g <- function() {

	sapply(input, function(x) x * 2.0);
	
	#for (i in 1:10000) {
	#		output[i] <- i * 2.0
	#	}
}


for (i in 1:100) {

	start <- nanotime()	
	g()
	end <- nanotime()
		
	print(end-start) 
}

#print(output)

