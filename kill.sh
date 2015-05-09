var=`sudo ps auxw | grep floodlight -m 1 | awk '{print $2}'`
sudo kill -9 $var


