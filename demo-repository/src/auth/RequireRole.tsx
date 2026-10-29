// AI-assisted (Claude), 2026; reviewed by the author.
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { Role } from '../api/auth';
import { useAuth } from './AuthContext';
import { HOME, ROLE_LABEL } from './roles';

/**
 * Wraps the routes of one kind of user. Signed-out visitors go to /login and come back afterwards
 * (query string included); a signed-in user with another role goes to their own start page with a
 * short notice. This only chooses screens: the server enforces every role on its own.
 */
const RequireRole = ({ role }: { role: Role }) => {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="py-24 text-center text-gray-500" role="status" aria-live="polite">
        Checking your session…
      </div>
    );
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (user.role !== role) {
    return <Navigate to={HOME[user.role]} replace
      state={{ notice: `That page is for ${ROLE_LABEL[role]} accounts. You are signed in as a ${ROLE_LABEL[user.role]}.` }} />;
  }
  return <Outlet />;
};

export default RequireRole;
