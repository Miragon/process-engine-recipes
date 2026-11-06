-- One database and one user per service. Local demo credentials only.
CREATE USER taskmanager WITH PASSWORD 'taskmanager';
CREATE DATABASE taskmanager OWNER taskmanager;

CREATE USER orders WITH PASSWORD 'orders';
CREATE DATABASE orders OWNER orders;

CREATE USER payments WITH PASSWORD 'payments';
CREATE DATABASE payments OWNER payments;
