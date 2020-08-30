
    create table apppackage (
        id varchar(255) not null,
        app_pkg_id  varchar(255) not null,
        app_pkg_name varchar(255) not null,
        app_pkg_version varchar(255) not null,
        app_pkg_path varchar(255) not null,
        app_provider varchar(200),
        app_pkg_desc varchar(500),
        app_pkg_affinity varchar(200),
        app_icon_url varchar(255),
        app_id varchar(255) not null,
        tenant_id varchar(255) not null,
        local_file_path varchar(2000),
        created_time varchar(200),
        modified_time varchar(200),
        primary key (id)
    );

    create table apppackagehost (
        id INT GENERATED BY DEFAULT AS IDENTITY,
        pkg_host_key varchar(255) not null,
        host_ip varchar(255) not null,
        app_pkg_id varchar(255) not null,
        distribution_status varchar(200) not null,
        tenant_id varchar(200) not null,
        error varchar(2000),
        primary key (id)
    );