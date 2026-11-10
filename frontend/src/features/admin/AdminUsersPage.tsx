import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { adminApi } from '../../api/endpoints';
import type { Role } from '../../api/types';
import { Alert } from '../../components/ui/form';
import { buttonClass } from '../../components/ui/styles';
import { useAuth } from '../auth/authContext';
import { describeError } from '../auth/errorMessages';

const PAGE_SIZE = 20;

export function AdminUsersPage() {
  const { user: me } = useAuth();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState('');
  const [query, setQuery] = useState('');
  const [role, setRole] = useState<Role | ''>('');
  const [page, setPage] = useState(1);

  const users = useQuery({
    queryKey: ['admin', 'users', query, role, page],
    queryFn: () => adminApi.searchUsers({ q: query, role: role || undefined, page, pageSize: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  const changeRole = useMutation({
    mutationFn: ({ id, newRole }: { id: string; newRole: Role }) => adminApi.changeRole(id, newRole),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
  });

  function onSearch(event: FormEvent) {
    event.preventDefault();
    setPage(1);
    setQuery(draft);
  }

  const totalPages = users.data ? Math.max(1, Math.ceil(users.data.total / PAGE_SIZE)) : 1;

  return (
    <div>
      <h2 className="nb-heading text-2xl">Users</h2>

      <form onSubmit={onSearch} role="search" className="mt-5 flex flex-col gap-4 sm:flex-row sm:items-end">
        <div className="flex-1">
          <label htmlFor="user-search" className="nb-label">
            Search by name or email
          </label>
          <input id="user-search" type="search" value={draft} onChange={(e) => setDraft(e.target.value)} className="nb-input" />
        </div>
        <div>
          <label htmlFor="role-filter" className="nb-label">
            Role
          </label>
          <select
            id="role-filter"
            value={role}
            onChange={(e) => {
              setPage(1);
              setRole(e.target.value as Role | '');
            }}
            className="nb-input"
          >
            <option value="">All</option>
            <option value="USER">User</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>
        <button type="submit" className={buttonClass.primary}>
          Search
        </button>
      </form>

      {users.isError && (
        <div className="mt-6">
          <Alert>{describeError(users.error)}</Alert>
        </div>
      )}
      {changeRole.isError && (
        <div className="mt-6">
          <Alert>{describeError(changeRole.error)}</Alert>
        </div>
      )}

      <div className="nb-card mt-6 overflow-x-auto">
        <table className="nb-table min-w-full text-sm">
          <caption className="sr-only">Users, newest first</caption>
          <thead>
            <tr>
              <th scope="col">Name</th>
              <th scope="col">Email</th>
              <th scope="col">Role</th>
              <th scope="col">Joined</th>
              <th scope="col">
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {users.data?.items.map((row) => {
              const isMe = row.id === me?.id;
              const target: Role = row.role === 'ADMIN' ? 'USER' : 'ADMIN';
              return (
                <tr key={row.id}>
                  <td className="font-bold">{row.name}</td>
                  <td className="break-all">{row.email}</td>
                  <td>
                    <span className={`nb-badge ${row.role === 'ADMIN' ? 'bg-nb-violet' : 'bg-nb-green'}`}>
                      {row.role === 'ADMIN' ? 'Admin' : 'User'}
                    </span>
                  </td>
                  <td className="whitespace-nowrap font-mono text-xs">{new Date(row.createdAt).toLocaleDateString()}</td>
                  <td className="text-right">
                    {isMe ? (
                      <span className="font-mono text-xs font-bold">You</span>
                    ) : (
                      <button
                        type="button"
                        className={`${buttonClass.secondary} nb-btn-sm`}
                        disabled={changeRole.isPending}
                        onClick={() => changeRole.mutate({ id: row.id, newRole: target })}
                        aria-label={`${target === 'ADMIN' ? 'Make admin' : 'Remove admin'}: ${row.name}`}
                      >
                        {target === 'ADMIN' ? 'Make admin' : 'Remove admin'}
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
            {users.data && users.data.items.length === 0 && (
              <tr>
                <td colSpan={5} className="py-6 text-center font-medium">
                  No users match.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <nav aria-label="Pagination" className="mt-5 flex items-center justify-between gap-3 text-sm">
        <span className="font-mono font-bold">
          {users.data ? `Page ${page} of ${totalPages} · ${users.data.total} users` : 'Loading…'}
        </span>
        <span className="flex gap-3">
          <button type="button" className={`${buttonClass.secondary} nb-btn-sm`} disabled={page <= 1} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <button type="button" className={`${buttonClass.secondary} nb-btn-sm`} disabled={page >= totalPages} onClick={() => setPage(page + 1)}>
            Next
          </button>
        </span>
      </nav>
    </div>
  );
}
