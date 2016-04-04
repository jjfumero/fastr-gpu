default: parallel

all:
	mx build 

parallel:
	mx build -p

update:
	mx sforceimports

clean:
	mx clean

