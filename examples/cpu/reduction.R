a <- 1:1000

f <- function(x, y) {
	x + y;
}

r <- sum(a);
print(r);

for (i in seq(1,10)) {
        start <- nanotime()
        reduction <- marawacc.reduce(a, f, neutral=0)
        result <- marawacc.execute(reduction)
        end <- nanotime()
    	print(identical(r, result))
        print(end-start);
}


print("FastR")
for (i in seq(1,10)) {
       start <- nanotime()
       r <- sum(a);
       end <- nanotime()
       print(end-start);
}

#print(result) 

