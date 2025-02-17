package example.jooq.reactive;

import example.domain.IOwner;
import example.reactive.IOwnerRepository;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Publisher;
import org.jooq.Record;
import org.jooq.Record3;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.transaction.Transactional;
import java.util.function.Function;

@Singleton
public class OwnerRepository extends AbstractRepository implements IOwnerRepository {

    private final static Table<Record> OWNER_TABLE = DSL.table("owners");
    private final static Field<Long> OWNER_ID = DSL.field("id", SQLDataType.BIGINT.identity(true));
    private final static Field<String> OWNER_NAME = DSL.field("name", SQLDataType.VARCHAR(200));
    private final static Field<Integer> OWNER_AGE = DSL.field("age", SQLDataType.INTEGER);

    public OwnerRepository(DSLContext db) {
        super(db);
    }

    @Override
    public Mono<Void> init() {
        return Mono.from(ctx.createTable(OWNER_TABLE).column(OWNER_ID).column(OWNER_NAME).column(OWNER_AGE)).then();
    }

    @Override
    public Owner create() {
        return new Owner();
    }

    @Transactional(Transactional.TxType.MANDATORY)
    @Override
    public Mono<Void> save(IOwner owner) {
        return withDSLContextMono(db -> db.insertInto(OWNER_TABLE)
                .columns(OWNER_NAME, OWNER_AGE)
                .values(owner.getName(), owner.getAge())
                .returning(OWNER_ID)
        ).doOnNext(q -> owner.setId(q.get(OWNER_ID))).then();
    }

    @Override
    public Mono<IOwner> findById(Long id) {
        return selectOwners(q -> q.where(OWNER_ID.eq(id))).next().map(this::map);
    }

    @Override
    public Flux<IOwner> findAll() {
        return selectOwners(q -> q).map(this::map);
    }

    @Override
    public Mono<IOwner> findByName(String name) {
        return selectOwners(q -> q.where(OWNER_NAME.eq(name))).next().map(this::map);
    }

    private Flux<Record3<Long, String, Integer>> selectOwners(Function<SelectJoinStep<Record3<Long, String, Integer>>, Publisher<Record3<Long, String, Integer>>> fn) {
        return withDSLContextFlux(dslContext -> fn.apply(dslContext.select(OWNER_ID, OWNER_NAME, OWNER_AGE).from(OWNER_TABLE)));
    }

    private IOwner map(Record3<Long, String, Integer> record) {
        return new Owner(record.get(OWNER_ID),
            record.get(OWNER_NAME),
            record.get(OWNER_AGE));
    }

}
