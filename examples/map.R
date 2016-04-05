a <- 1:1000000
b <- 1:1000000

f <- function(x) {
        a <- 0;
        for (i in seq(1,1000)) {
       		a <- x * 2.0;
        }
        return(a);
}

g <- function(x) {
        a <- 0;
        for (i in seq(1,1000)) {
            a <- x * 2.0;
        }
        return(a);
}

r <- sapply(a, g);

for (i in seq(1,10)) {
        start <- nanotime()
        m1 <- marawacc.map(a, f, nThreads=4)
		result <- marawacc.execute(m1) 
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

