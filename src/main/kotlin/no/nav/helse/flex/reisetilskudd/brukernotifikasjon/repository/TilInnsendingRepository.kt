package no.nav.helse.flex.reisetilskudd.brukernotifikasjon.repository

import no.nav.helse.flex.reisetilskudd.brukernotifikasjon.domain.TilInnsending
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TilInnsendingRepository : CrudRepository<TilInnsending, String> {
    fun findTilInnsendingByReisetilskuddId(reisetilskuddId: String): TilInnsending?
    fun existsByReisetilskuddId(reisetilskuddId: String): Boolean
}
