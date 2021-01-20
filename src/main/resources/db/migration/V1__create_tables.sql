CREATE TABLE til_utfylling
(
    reisetilskudd_id VARCHAR(36) PRIMARY KEY,
    nokkel VARCHAR(36),
    grupperingsid VARCHAR(36),
    fnr VARCHAR(11) NOT NULL,
    eksternt_varsel BOOLEAN,
    beskjed_sendt TIMESTAMP WITH TIME ZONE NOT NULL,
    done_sendt TIMESTAMP WITH TIME ZONE
);

CREATE TABLE til_innsending
(
    reisetilskudd_id VARCHAR(36) PRIMARY KEY,
    nokkel VARCHAR(36),
    grupperingsid VARCHAR(36),
    fnr VARCHAR(11) NOT NULL,
    eksternt_varsel BOOLEAN,
    oppgave_sendt TIMESTAMP WITH TIME ZONE NOT NULL,
    done_sendt TIMESTAMP WITH TIME ZONE
);

