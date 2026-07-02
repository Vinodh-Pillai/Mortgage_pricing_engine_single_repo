import { useAuth } from '../../lib/auth/AuthContext';

export function UserAccountScreen({ mode }: { mode: 'profile' | 'settings' }) {
  const { currentUser, currentPersona, isLoading, authError, refreshCurrentUser } = useAuth();
  const title = mode === 'profile' ? 'User Profile' : 'User Settings';
  const profileAvailable = Boolean(currentUser);

  return (
    <main className="functionality-page" data-screen-id={`user-${mode}`} aria-labelledby="user-account-title">
      <section className="hero" aria-labelledby="user-account-title">
        <p className="eyebrow">Account contract</p>
        <h1 id="user-account-title">{title}</h1>
        <p>Account identity is read from the existing <code>/api/auth/me</code> contract. Settings write behavior stays blocked until an account settings contract is supplied.</p>
      </section>

      {isLoading ? <p role="status">Loading account contract...</p> : null}
      {authError ? <div className="banner banner--blocked" role="alert">{authError}</div> : null}

      <section className="panel" aria-labelledby="user-profile-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Profile</p>
            <h2 id="user-profile-heading">Authenticated user</h2>
          </div>
          <button type="button" onClick={() => void refreshCurrentUser()}>Refresh profile</button>
        </div>
        {profileAvailable ? (
          <dl className="status-grid">
            <dt>Name</dt><dd>{currentUser?.fullName || currentUser?.name || 'Not supplied'}</dd>
            <dt>Email</dt><dd>{currentUser?.email || 'Not supplied'}</dd>
            <dt>Role</dt><dd>{currentUser?.role || 'Not supplied'}</dd>
            <dt>Persona</dt><dd>{currentPersona?.role || 'Not supplied'}</dd>
          </dl>
        ) : (
          <div className="banner banner--blocked" role="alert">
            <strong>Profile blocked</strong>
            <span>The auth/account contract did not return a current user. The UI is not showing local placeholder profile data.</span>
          </div>
        )}
      </section>

      {mode === 'settings' ? (
        <section className="panel" aria-labelledby="user-settings-heading">
          <h2 id="user-settings-heading">Settings contract state</h2>
          <div className="banner banner--blocked" role="alert">
            <strong>Settings write contract required</strong>
            <span>No backend account settings read/write contract is present in the scoped UI API clients. Profile data remains read-only from <code>/api/auth/me</code>.</span>
          </div>
        </section>
      ) : null}
    </main>
  );
}

export function UserProfileScreen() {
  return <UserAccountScreen mode="profile" />;
}

export function UserSettingsScreen() {
  return <UserAccountScreen mode="settings" />;
}

export default UserAccountScreen;
