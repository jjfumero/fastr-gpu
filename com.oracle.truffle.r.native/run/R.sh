#!/bin/bash
#
# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# Startup FastR (R) using the mx tool (development)
# This is exec'ed by the (generic) R script in the parent directory.
#

#echo args="$@"
#printenv | fgrep R_

source="${BASH_SOURCE[0]}"
while [ -h "$source" ] ; do source="$(readlink "$source")"; done
PRIMARY_PATH="$( cd -P "$( dirname "$source" )" && pwd )"/../..

mx=`which mx`
if [ -z "$mx"] ; then
    if [ -z "$MX_HOME" ] ; then
	echo "Error: mx cannot be found: add to PATH or set MX_HOME"
	exit 1
    else
	mx=$MX_HOME/mx
    fi
fi

exec $mx $MX_R_GLOBAL_ARGS R $MX_R_CMD_ARGS "$@"
