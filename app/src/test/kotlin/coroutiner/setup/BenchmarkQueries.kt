package coroutiner.setup

object BenchmarkQueries {
    
    const val baseQuery =
        "SELECT u.id as user_id, u.name as user_name, g.id as group_id, g.name as group_name, u.created as user_created, u.updated as user_updated FROM users u JOIN groups g ON g.id = u.group_id"
    
}