package example.jooq.reactive;

import example.domain.IPet;
import example.reactive.IPetRepository;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Publisher;
import org.jooq.Record;
import org.jooq.Record4;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import java.util.Optional;
import java.util.function.Function;

@Singleton
public class PetRepository extends AbstractRepository implements IPetRepository {

    private final OwnerRepository ownerRepository;

    private final static Table<Record> PET_TABLE = DSL.table("pets");
    private final static Field<Long> PET_ID = DSL.field("id", SQLDataType.BIGINT.identity(true));
    private final static Field<String> PET_NAME = DSL.field("name", SQLDataType.VARCHAR(200));
    private final static Field<String> PET_TYPE = DSL.field("type", SQLDataType.VARCHAR(200));
    private final static Field<Long> PET_OWNER = DSL.field("owner", SQLDataType.BIGINT);

    public PetRepository(DSLContext db, OwnerRepository ownerRepository) {
        super(db);
        this.ownerRepository = ownerRepository;
    }

    @Override
    public Mono<Void> init() {
        return Mono.from(ctx.createTable(PET_TABLE).column(PET_ID).column(PET_NAME).column(PET_TYPE).column(PET_OWNER)).then();
    }

    @Override
    public IPet create() {
        return new Pet();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    @Override
    public Mono<Void> save(IPet pet) {
        return withDSLContextMono(dslContext -> ctx.insertInto(PET_TABLE)
            .columns(PET_NAME, PET_TYPE, PET_OWNER)
            .values(pet.getName(), pet.getType() == null ? null : pet.getType().name(), pet.getOwner().getId())
            .returning(PET_ID)).map(q -> q.get(PET_ID))
            .doOnNext(pet::setId)
            .then();
    }

    @Override
    public Flux<IPet> findAll() {
        return selectPets(q -> q).flatMap(this::map).map(x -> x);
    }

    @Override
    public Mono<IPet> findByName(String name) {
        return selectPets(q -> q.where(PET_NAME.eq(name))).next().flatMap(this::map);
    }

    private Flux<Record4<Long, String, String, Long>> selectPets(Function<SelectJoinStep<Record4<Long, String, String, Long>>, Publisher<Record4<Long, String, String, Long>>> fn) {
        return withDSLContextFlux(dslContext -> fn.apply(dslContext.select(PET_ID, PET_NAME, PET_TYPE, PET_OWNER).from(PET_TABLE)));
    }

    private Mono<IPet> map(Record4<Long, String, String, Long> record) {
        return ownerRepository.findById(record.get(PET_OWNER)).map(owner -> new Pet(record.get(PET_ID),
            record.get(PET_NAME),
            Optional.ofNullable(record.get(PET_TYPE)).map(IPet.PetType::valueOf).orElse(null), (Owner) owner));

    }

}
