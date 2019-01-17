
# In the R interpreter
```R
f <- function(x, y) x + y

marawacc.init(()

size <- 100
mapply(f, runif(size), runif(size))
```


# ####################################################
# Performance
# ####################################################
# Running sequentially (FastR)
./run benchmarks/fastR/nbody/nbodySeq.R 16000

# Running on the GPU
./run benchmarks/fastR/nbody/nbodyGPU.R 16000

## ###################################################
## Deoptimizatoin
## ###################################################
./run examples/deopt/deopt01.R 1024

