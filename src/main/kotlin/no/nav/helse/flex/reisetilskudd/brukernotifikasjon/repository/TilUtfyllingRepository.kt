package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.repository

import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.TilUtfylling
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TilUtfyllingRepository : CrudRepository<TilUtfylling, String> {
    fun findTilUtfyllingByReisetilskuddId(reisetilskuddId: String): TilUtfylling?
}
