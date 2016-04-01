a <- 1:1000000
b <- 1:1000000

sumArrays <- function(x) {
        a <- 0;
        for (i in seq(1,1000)) {
       		a <- x * 2.0;
        	#b <- sqrt(a * a) + log(a) / log(1/a);
	        #a <- b * a;
        }
        return(a);
}

sumArrays2 <- function(x) {
        a <- 0;
        for (i in seq(1,1000)) {
            a <- x * 2.0;
        	#b <- sqrt(a * a) + log(a) / log(1/a);
        	#a <- b * a;
        }
        return(a);
}

r <- sapply(a, sumArrays2);

for (i in seq(1,10)) {
        start <- nanotime()
        result <- marawacc.parallelMap(a, sumArrays, nThreads=4)
        end <- nanotime()
    	print(identical(r, result))
        print(end-start);
}


print("Sapply")
for (i in seq(1,10)) {
       start <- nanotime()
       r <- sapply(a, sumArrays2);
       end <- nanotime()
       print(end-start);
}

#print(result) 

