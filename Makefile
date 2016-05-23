default: parallel

all:
	mx build 

parallel:
	mx build -p

update:
	mx sforceimports

eclipse:
	mx eclipseinit 

sample:
	./run examples/map.R 

clean:
	mx clean


