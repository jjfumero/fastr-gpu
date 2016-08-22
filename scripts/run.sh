
# Saxpy 8M
./runbench benchmarks/fastR/saxpy/saxpy.R 1048576 > saxpyFASTR.log 
./runbench benchmarks/fastR/saxpy/saxpyGPUPArrays.R 1048576 > saxpyASTx.log
$GNUR/Rscript benchmarks/fastR/saxpy/saxpyGNU.R 1048576 > saxpyGNU.log

# BS 1M
./runbench benchmarks/fastR/blackscholes/blackscholes.R 1048576 > bsFASTR.log 
./runbench benchmarks/fastR/blackscholes/blackscholesGPUPArrays.R 1048576 > bsASTx.log
$GNUR/Rscript benchmarks/fastR/blackscholes/blackscholesGNU.R 1048576 > bsGNU.log

# BS 4M
./runbench benchmarks/fastR/blackscholes/blackscholes.R 4194304 > bsFASTR4.log 
./runbench benchmarks/fastR/blackscholes/blackscholesGPUPArrays.R 4194304 > bsASTx4.log
$GNUR/Rscript benchmarks/fastR/blackscholes/blackscholesGNU.R 4194304 > bsGNU4.log


# NBody 65536
./runbench benchmarks/fastR/nbody/nbody.R 65536 > nbodyFastR.log
./runbench benchmarks/fastR/nbody/nbodyGPUPArrays.R 65536 > nbodyASTx.log
$GNUR/Rscript benchmarks/fastR/nbody/nbodyGNU.R 65536 > nbodyGNU.log
