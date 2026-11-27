using System.Linq;
using System.Text;
using Newtonsoft.Json;

namespace StoneRift.Core
{
    public static class Hashing
    {
        /// <summary>FNV-1a 64-bit: tiny, fast and identical on every platform.</summary>
        public static ulong Fnv1a64(string text)
        {
            const ulong offset = 14695981039346656037UL;
            const ulong prime = 1099511628211UL;
            ulong h = offset;
            foreach (byte b in Encoding.UTF8.GetBytes(text))
            {
                h ^= b;
                h *= prime;
            }
            return h;
        }

        /// <summary>
        /// Fingerprint of the card catalogue. Two phones can only play together when their cards are identical,
        /// otherwise the same action would give different results on each phone.
        /// </summary>
        public static ulong ContentHash(this CardDatabase db) =>
            Fnv1a64(JsonConvert.SerializeObject(db.Cards.OrderBy(c => c.Id, System.StringComparer.Ordinal)));
    }
}
