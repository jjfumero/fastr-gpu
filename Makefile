default: parallel

all:
	mx build 

parallel:
	mx build -p

update:
	mx sforceimports

eclipse:
	mx eclipseinit 

testOCL:
	./scripts/runBenchTest.sh 

unittests:
	./run examples/unittestTuples.R

sample:
	./run benchmarks/fastR/saxpy/saxpyGPU.R 1000

clean:
	mx clean
