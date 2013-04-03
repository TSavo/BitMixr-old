create table Payment (id varchar(255) not null primary key, sourceAddress varchar(255), destinationAddress varchar(255), recievedAmount double, sentAmount double, visible bit(1)) ENGINE=InnoDB;
