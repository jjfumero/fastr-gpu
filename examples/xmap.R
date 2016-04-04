a <- 1:1000000
b <- 1:1000000

f <- function(x) {
        a <- 0;
        for (i in seq(1,1000)) {
       		a <- x * 2.0;
        	#b <- sqrt(a * a) + log(a) / log(1/a);
	        #a <- b * a;
        }
        return(a);
}

g <- function(x) {
        a <- 0;
        for (i in seq(1,1000)) {
            a <- x * 2.0;
        	#b <- sqrt(a * a) + log(a) / log(1/a);
        	#a <- b * a;
        }
        return(a);
}

r <- sapply(a, g);

for (i in seq(1,10)) {
        start <- nanotime()
        result <- marawacc.parallelMap(a, f, nThreads=4)
        end <- nanotime()
    	print(identical(r, result))
        print(end-start);
}


print("Sapply")
for (i in seq(1,10)) {
       start <- nanotime()
       r <- sapply(a, g);
       end <- nanotime()
       print(end-start);
}

#print(result) 

