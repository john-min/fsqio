// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.lift

import com.mongodb.{BasicDBObject, WriteConcern}
import com.mongodb.client.MongoCollection
import io.fsq.field.Field
import io.fsq.rogue.{AddLimit, FindAndModifyQuery, Iter, Limited, ModifyQuery, Query,
    QueryExecutor => LegacyQueryExecutor, RequireShardKey, Required, ShardingOk, Skipped, Unlimited, Unselected,
    Unskipped}
import io.fsq.rogue.MongoHelpers.MongoSelect
import io.fsq.rogue.Rogue._
import io.fsq.rogue.adapter.BlockingResult
import io.fsq.rogue.query.{QueryExecutor => NewQueryExecutor}


// TODO(jacob): Either delete this file in favor of using a QueryExecutor explicitly, or
//    move it out of the lift package.

case class ExecutableQueryConfig[MB, RB](
  legacyQueryExecutor: LegacyQueryExecutor[MB, RB],
  newQueryExecutor: NewQueryExecutor[MongoCollection, Object, BasicDBObject, MB, RB, BlockingResult],
  useNewQueryExecutor: () => Boolean
)

case class ExecutableQuery[MB, M <: MB, RB, R, State](
  query: Query[M, R, State],
  config: ExecutableQueryConfig[MB, RB]
)(
  implicit ev: ShardingOk[M, State]
) extends BlockingResult.Implicits {

  /**
   * Gets the size of the query result. This should only be called on queries that do not
   * have limits or skips.
   */
  def count(): Long = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.count(query)
    } else {
      config.legacyQueryExecutor.count(query)
    }
  }

  /**
   * Returns the number of distinct values returned by a query. The query must not have
   * limit or skip clauses.
   */
  def countDistinct[V](field: M => Field[V, _]): Long = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.countDistinct(query)(field.asInstanceOf[M => Field[V, M]])
    } else {
      config.legacyQueryExecutor.countDistinct(query)(field.asInstanceOf[M => Field[V, M]])
    }
  }

  /**
   * Returns a list of distinct values returned by a query. The query must not have
   * limit or skip clauses.
   */
  def distinct[V](field: M => Field[V, _]): Seq[V] = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.distinct(query)(field.asInstanceOf[M => Field[V, M]])
    } else {
      config.legacyQueryExecutor.distinct(query)(field.asInstanceOf[M => Field[V, M]])
    }
  }

  /**
   * Checks if there are any records that match this query.
   */
  def exists()(implicit ev: State <:< Unlimited with Unskipped): Boolean = {
    val q = query.copy(select = Some(MongoSelect[M, Null](Nil, _ => null)))
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.fetch(q.limit(1)).nonEmpty
    } else {
      config.legacyQueryExecutor.fetch(q.limit(1)).nonEmpty
    }
  }

  /**
   * Executes a function on each record value returned by a query.
   * @param f a function to be invoked on each fetched record.
   * @return nothing.
   */
  def foreach(f: R => Unit): Unit = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.foreach(query)(f)
    } else {
      config.legacyQueryExecutor.foreach(query)(f)
    }
  }

  /**
   * Execute the query, returning all of the records that match the query.
   * @return a list containing the records that match the query
   */
  def fetch(): List[R] = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.fetch[M, R, State, List](query)
    } else {
      config.legacyQueryExecutor.fetchList(query)
    }
  }

  /**
   * Execute a query, returning no more than a specified number of result records. The
   * query must not have a limit clause.
   * @param limit the maximum number of records to return.
   */
  def fetch[S2](limit: Int)(implicit ev1: AddLimit[State, S2], ev2: ShardingOk[M, S2]): List[R] = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.fetch[M, R, S2, List](query.limit(limit))
    } else {
      config.legacyQueryExecutor.fetchList(query.limit(limit))
    }
  }

  /**
   * fetch a batch of results, and execute a function on each element of the list.
   * @param f the function to invoke on the records that match the query.
   * @return a list containing the results of invoking the function on each record.
   */
  def fetchBatch[T](batchSize: Int)(f: Seq[R] => Seq[T]): Seq[T] = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.fetchBatch[M, R, State, Vector, T](query, batchSize)(f)
    } else {
      config.legacyQueryExecutor.fetchBatch(query, batchSize)(f)
    }
  }

  /**
   * Fetches the first record that matches the query. The query must not contain a "limited" clause.
   * @return an option record containing either the first result that matches the
   *         query, or None if there are no records that match.
   */
  def get[S2]()(implicit ev1: AddLimit[State, S2], ev2: ShardingOk[M, S2]): Option[R] = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.fetchOne(query)
    } else {
      config.legacyQueryExecutor.fetchOne(query)
    }
  }

  /**
   * Fetches the records that match the query in paginated form. The query must not contain
   * a "limit" clause.
   * @param countPerPage the number of records to be contained in each page of the result.
   */
  def paginate(countPerPage: Int)
              (implicit ev1: Required[State, Unlimited with Unskipped],
               ev2: ShardingOk[M, State]): PaginatedQuery[MB, M, RB, R, Unlimited with Unskipped] = {
    new PaginatedQuery(ev1(query), config, countPerPage)
  }

  /**
   * Delete all of the records that match the query. The query must not contain any "skip",
   * "limit", or "select" clauses. Sends the delete operation to mongo, and returns - does
   * <em>not</em> wait for the delete to be finished.
   */
  def bulkDelete_!!!()
                    (implicit ev1: Required[State, Unselected with Unlimited with Unskipped]): Unit = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.bulkDelete_!!(query)
    } else {
      config.legacyQueryExecutor.bulkDelete_!!(query)
    }
  }

  /**
   * Delete all of the records that match the query. The query must not contain any "skip",
   * "limit", or "select" clauses. Sends the delete operation to mongo, and waits for the
   * delete operation to complete before returning to the caller.
   */
  def bulkDelete_!!(concern: WriteConcern)
                   (implicit ev1: Required[State, Unselected with Unlimited with Unskipped]): Unit = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.bulkDelete_!!(query, concern)
    } else {
      config.legacyQueryExecutor.bulkDelete_!!(query, concern)
    }
  }

  /**
   * Finds the first record that matches the query (if any), fetches it, and then deletes it.
   * A copy of the deleted record is returned to the caller.
   */
  def findAndDeleteOne()(implicit ev: RequireShardKey[M, State]): Option[R] = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.findAndDeleteOne(query)
    } else {
      config.legacyQueryExecutor.findAndDeleteOne(query)
    }
  }

  /**
   * Return a string containing details about how the query would be executed in mongo.
   * In particular, this is useful for finding out what indexes will be used by the query.
   */
  def explain(): String = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.explain(query)
    } else {
      config.legacyQueryExecutor.explain(query)
    }
  }

  def iterate[S](state: S)(handler: (S, Iter.Event[R]) => Iter.Command[S]): S = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.iterate(query, state)(handler)
    } else {
      config.legacyQueryExecutor.iterate(query, state)(handler)
    }
  }

  def iterateBatch[S](batchSize: Int, state: S)(handler: (S, Iter.Event[Seq[R]]) => Iter.Command[S]): S = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.iterateBatch(query, batchSize, state)(handler)
    } else {
      config.legacyQueryExecutor.iterateBatch(query, batchSize, state)(handler)
    }
  }
}

case class ExecutableModifyQuery[MB, M <: MB, RB, State](
  query: ModifyQuery[M, State],
  config: ExecutableQueryConfig[MB, RB]
) extends BlockingResult.Implicits {

  def updateMulti(): Unit = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.updateMany(query)
    } else {
      config.legacyQueryExecutor.updateMulti(query)
    }
  }

  def updateOne()(implicit ev: RequireShardKey[M, State]): Unit = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.updateOne(query)
    } else {
      config.legacyQueryExecutor.updateOne(query)
    }
  }

  def upsertOne()(implicit ev: RequireShardKey[M, State]): Unit = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.upsertOne(query)
    } else {
      config.legacyQueryExecutor.upsertOne(query)
    }
  }

  def updateMulti(writeConcern: WriteConcern): Unit = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.updateMany(query, writeConcern)
    } else {
      config.legacyQueryExecutor.updateMulti(query, writeConcern)
    }
  }

  def updateOne(writeConcern: WriteConcern)(implicit ev: RequireShardKey[M, State]): Unit = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.updateOne(query, writeConcern)
    } else {
      config.legacyQueryExecutor.updateOne(query, writeConcern)
    }
  }

  def upsertOne(writeConcern: WriteConcern)(implicit ev: RequireShardKey[M, State]): Unit = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.upsertOne(query, writeConcern)
    } else {
      config.legacyQueryExecutor.upsertOne(query, writeConcern)
    }
  }
}

case class ExecutableFindAndModifyQuery[MB, M <: MB, RB, R](
  query: FindAndModifyQuery[M, R],
  config: ExecutableQueryConfig[MB, RB]
) extends BlockingResult.Implicits {

  def updateOne(returnNew: Boolean = false): Option[R] = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.findAndUpdateOne(query, returnNew)
    } else {
      config.legacyQueryExecutor.findAndUpdateOne(query, returnNew)
    }
  }

  def upsertOne(returnNew: Boolean = false): Option[R] = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.findAndUpsertOne(query, returnNew)
    } else {
      config.legacyQueryExecutor.findAndUpsertOne(query, returnNew)
    }
  }
}

class PaginatedQuery[MB, M <: MB, RB, R, +State <: Unlimited with Unskipped](
  q: Query[M, R, State],
  config: ExecutableQueryConfig[MB, RB],
  val countPerPage: Int,
  val pageNum: Int = 1
)(
  implicit ev: ShardingOk[M, State]
) extends BlockingResult.Implicits {

  def copy() = new PaginatedQuery(q, config, countPerPage, pageNum)

  def setPage(p: Int) = if (p == pageNum) this else new PaginatedQuery(q, config, countPerPage, p)

  def setCountPerPage(c: Int) = if (c == countPerPage) this else new PaginatedQuery(q, config, c, pageNum)

  lazy val countAll: Long = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.count(q)
    } else {
      config.legacyQueryExecutor.count(q)
    }
  }

  def fetch(): List[R] = {
    if (config.useNewQueryExecutor()) {
      config.newQueryExecutor.fetch[M, R, Limited with Skipped, List](
        q.skip(countPerPage * (pageNum - 1)).limit(countPerPage)
      )
    } else {
      config.legacyQueryExecutor.fetchList(q.skip(countPerPage * (pageNum - 1)).limit(countPerPage))
    }
  }

  def numPages = math.ceil(countAll.toDouble / countPerPage.toDouble).toInt max 1
}
