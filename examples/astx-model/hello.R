

# Asynchronous tasks
id1 <- astx.async( function(x) { Sys.sleep(4); print("Hello thread 1"); } , 1:2)
id2 <- astx.async( function(x) { Sys.sleep(5); print("Hello thread 2"); } , 1:2)
id3 <- astx.async( function(x) { Sys.sleep(6); print("Hello thread 3"); } , 1:2)

# Synchronization point 
astx.sync(c(id1, id2, id3)) 


