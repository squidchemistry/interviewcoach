-- Atomic token bucket (one round trip, no race between read and write).
-- KEYS[1]  bucket key
-- ARGV[1]  capacity (max tokens)
-- ARGV[2]  period in ms to refill a full bucket
-- ARGV[3]  cost of this request
-- Returns { allowed (1|0), remaining tokens (floor), retry-after ms, ms until bucket is full }
local capacity = tonumber(ARGV[1])
local period = tonumber(ARGV[2])
local cost = tonumber(ARGV[3])

-- Server clock, so all API replicas agree on time.
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(state[1])
local ts = tonumber(state[2])
if tokens == nil or ts == nil then
  tokens = capacity
  ts = now
end

local elapsed = math.max(0, now - ts)
tokens = math.min(capacity, tokens + elapsed * capacity / period)

local allowed = 0
local retry_after = 0
if tokens >= cost then
  tokens = tokens - cost
  allowed = 1
else
  retry_after = math.ceil((cost - tokens) * period / capacity)
end

redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
redis.call('PEXPIRE', KEYS[1], period)

local reset = math.ceil((capacity - tokens) * period / capacity)
return { allowed, math.floor(tokens), retry_after, reset }
