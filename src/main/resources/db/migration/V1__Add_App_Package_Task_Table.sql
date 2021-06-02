
    create table apppackage (
        id varchar(255) not null,
        app_pkg_id  varchar(255) not null,
        app_pkg_name varchar(255) not null,
        app_pkg_version varchar(255),
        app_pkg_path varchar(255),
        app_provider varchar(200),
        app_pkg_desc varchar(500),
        app_pkg_affinity varchar(200),
        app_icon_url varchar(255),
        app_id varchar(255) not null,
        tenant_id varchar(255) not null,
        created_time timestamp default current_timestamp,
        modified_time timestamp default current_timestamp,
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

    create table apmtenant (
        tenant  varchar(255) not null,
        primary key (tenant)
    );

    create table apppackageinfo (
        id varchar(255) not null,
        app_id  varchar(255) not null,
        appstore_ip  varchar(255) not null,
        package_id  varchar(255) not null,
        name varchar(255) not null,
        appstore_endpoint varchar(21) not null,
        pkg_size varchar(255),
        version varchar(255),
        type varchar(255),
        affinity varchar(255),
        industry varchar(255),
        contact varchar(255),
        user_id varchar(255),
        user_name varchar(255),
        status varchar(255),
        short_desc varchar(255),
        test_task_id varchar(255),
        provider varchar(255),
        sync_status varchar(255),
        created_time varchar(255),
        operational_info varchar(255),
        primary key (id)
    );

    create table apptemplate (
        template_id varchar(255) not null,
        app_name varchar(255) not null,
        version varchar(255) not null,
        app_package_id varchar(255) not null,
        app_id varchar(255) not null,
        tenant_id varchar(255) not null,
        primary key (template_id)
    );

    create table apptemplateinputattr (
        attr_id INT GENERATED BY DEFAULT AS IDENTITY,
        template_id varchar(255) not null,
        name varchar(255),
        type varchar(255),
        default_value varchar(255),
        description varchar(255),
        primary key (attr_id),
        constraint fk_apptemplate_app
          foreign key(template_id)
        	  references apptemplate(template_id)
    );

